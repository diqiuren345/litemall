package org.linlinjava.litemall.wx.web;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_NO_STOCK;
import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_UNSHELVE;

/**
 * 用户购物车服务
 */
@RestController
@RequestMapping("/wx/cart")
@Validated
public class WxCartController {
    private static final String MSG_GOODS_OFF_SHELF = "商品已下架";

    private final Log logger = LogFactory.getLog(WxCartController.class);

    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private LitemallGoodsService goodsService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallAddressService addressService;
    @Autowired
    private LitemallGrouponRulesService grouponRulesService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private CouponVerifyService couponVerifyService;

    /**
     * 用户购物车信息
     *
     * @param userId 用户ID
     * @return 用户购物车信息
     */
    @GetMapping("index")
    public Object index(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        List<LitemallCart> list = cartService.queryByUid(userId);
        List<LitemallCart> cartList = new ArrayList<>();
        // TODO
        // 如果系统检查商品已删除或已下架，则系统自动删除。
        // 更好的效果应该是告知用户商品失效，允许用户点击按钮来清除失效商品。
        for (LitemallCart cart : list) {
            LitemallGoods goods = goodsService.findById(cart.getGoodsId());
            if (goods == null || !goods.getIsOnSale()) {
                cartService.deleteById(cart.getId());
                logger.debug("系统自动删除失效购物车商品 goodsId=" + cart.getGoodsId() + " productId=" + cart.getProductId());
            }
            else{
                cartList.add(cart);
            }
        }

        Integer goodsCount = 0;
        BigDecimal goodsAmount = new BigDecimal(0.00);
        Integer checkedGoodsCount = 0;
        BigDecimal checkedGoodsAmount = new BigDecimal(0.00);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
            goodsAmount = goodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            if (cart.getChecked()) {
                checkedGoodsCount += cart.getNumber();
                checkedGoodsAmount = checkedGoodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }
        Map<String, Object> cartTotal = new HashMap<>();
        cartTotal.put("goodsCount", goodsCount);
        cartTotal.put("goodsAmount", goodsAmount);
        cartTotal.put("checkedGoodsCount", checkedGoodsCount);
        cartTotal.put("checkedGoodsAmount", checkedGoodsAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("cartList", cartList);
        result.put("cartTotal", cartTotal);

        return ResponseUtil.ok(result);
    }

    /**
     * 加入商品到购物车
     * <p>
     * 如果已经存在购物车货品，则增加数量；
     * 否则添加新的购物车货品项。
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { goodsId: xxx, productId: xxx, number: xxx }
     * @return 加入购物车操作结果
     */
    @PostMapping("add")
    public Object add(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (cart == null || !hasRequiredCartFields(cart)) {
            return ResponseUtil.badArgument();
        }
        LitemallGoods goods = goodsService.findById(cart.getGoodsId());
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, MSG_GOODS_OFF_SHELF);
        }
        LitemallGoodsProduct product = productService.findById(cart.getProductId());
        LitemallCart existCart = cartService.queryExist(cart.getGoodsId(), cart.getProductId(), userId);
        Object error = existCart == null
                ? addNewCartItem(userId, cart, goods, product)
                : increaseCartNumber(existCart, cart.getNumber(), product);
        if (error != null) {
            return error;
        }
        return goodscount(userId);
    }

    /**
     * 立即购买
     * <p>
     * 和add方法的区别在于：
     * 1. 如果购物车内已经存在购物车货品，前者的逻辑是数量添加，这里的逻辑是数量覆盖
     * 2. 添加成功以后，前者的逻辑是返回当前购物车商品数量，这里的逻辑是返回对应购物车项的ID
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { goodsId: xxx, productId: xxx, number: xxx }
     * @return 立即购买操作结果
     */
    @PostMapping("fastadd")
    public Object fastadd(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (cart == null || !hasRequiredCartFields(cart)) {
            return ResponseUtil.badArgument();
        }
        LitemallGoods goods = goodsService.findById(cart.getGoodsId());
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, MSG_GOODS_OFF_SHELF);
        }
        LitemallGoodsProduct product = productService.findById(cart.getProductId());
        LitemallCart existCart = cartService.queryExist(cart.getGoodsId(), cart.getProductId(), userId);
        Object error = existCart == null
                ? addNewCartItem(userId, cart, goods, product)
                : overwriteCartNumber(existCart, cart.getNumber(), product);
        if (error != null) {
            return error;
        }
        return ResponseUtil.ok(existCart != null ? existCart.getId() : cart.getId());
    }

    /**
     * 修改购物车商品货品数量
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { id: xxx, goodsId: xxx, productId: xxx, number: xxx }
     * @return 修改结果
     */
    @PostMapping("update")
    public Object update(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        Integer id = cart.getId();
        if (!ObjectUtils.allNotNull(id, productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //判断是否存在该订单
        // 如果不存在，直接返回错误
        LitemallCart existCart = cartService.findById(userId, id);
        if (existCart == null) {
            return ResponseUtil.badArgumentValue();
        }

        // 判断goodsId和productId是否与当前cart里的值一致
        if (!existCart.getGoodsId().equals(goodsId)) {
            return ResponseUtil.badArgumentValue();
        }
        if (!existCart.getProductId().equals(productId)) {
            return ResponseUtil.badArgumentValue();
        }

        //判断商品是否可以购买
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, MSG_GOODS_OFF_SHELF);
        }

        //取得规格的信息,判断规格库存
        LitemallGoodsProduct product = productService.findById(productId);
        if (product == null || product.getNumber() < number) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "库存不足");
        }

        existCart.setNumber(number.shortValue());
        if (cartService.updateById(existCart) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /**
     * 购物车商品货品勾选状态
     * <p>
     * 如果原来没有勾选，则设置勾选状态；如果商品已经勾选，则设置非勾选状态。
     *
     * @param userId 用户ID
     * @param body   购物车商品信息， { productIds: xxx, isChecked: 1/0 }
     * @return 购物车信息
     */
    @PostMapping("checked")
    public Object checked(@LoginUser Integer userId, @RequestBody String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");
        if (productIds == null) {
            return ResponseUtil.badArgument();
        }

        Integer checkValue = JacksonUtil.parseInteger(body, "isChecked");
        if (checkValue == null) {
            return ResponseUtil.badArgument();
        }
        Boolean isChecked = (checkValue == 1);

        cartService.updateCheck(userId, productIds, isChecked);
        return index(userId);
    }

    /**
     * 购物车商品删除
     *
     * @param userId 用户ID
     * @param body   购物车商品信息， { productIds: xxx }
     * @return 购物车信息
     * 成功则
     * {
     * errno: 0,
     * errmsg: '成功',
     * data: xxx
     * }
     * 失败则 { errno: XXX, errmsg: XXX }
     */
    @PostMapping("delete")
    public Object delete(@LoginUser Integer userId, @RequestBody String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");

        if (productIds == null || productIds.size() == 0) {
            return ResponseUtil.badArgument();
        }

        cartService.delete(productIds, userId);
        return index(userId);
    }

    /**
     * 购物车商品货品数量
     * <p>
     * 如果用户没有登录，则返回空数据。
     *
     * @param userId 用户ID
     * @return 购物车商品货品数量
     */
    @GetMapping("goodscount")
    public Object goodscount(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.ok(0);
        }

        int goodsCount = 0;
        List<LitemallCart> cartList = cartService.queryByUid(userId);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
        }

        return ResponseUtil.ok(goodsCount);
    }

    /**
     * 购物车下单
     *
     * @param userId    用户ID
     * @param cartId    购物车商品ID：
     *                  如果购物车商品ID是空，则下单当前用户所有购物车商品；
     *                  如果购物车商品ID非空，则只下单当前购物车商品。
     * @param addressId 收货地址ID：
     *                  如果收货地址ID是空，则查询当前用户的默认地址。
     * @param couponId  优惠券ID：
     *                  如果优惠券ID是空，则自动选择合适的优惠券。
     * @return 购物车操作结果
     */
    @GetMapping("checkout")
    public Object checkout(@LoginUser Integer userId, Integer cartId, Integer addressId, Integer couponId,
                           Integer userCouponId, Integer grouponRulesId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // 收货地址：指定地址 -> 默认地址 -> 空地址(id=0)
        LitemallAddress checkedAddress = resolveAddress(userId, addressId);
        addressId = checkedAddress.getId();

        // 团购优惠
        BigDecimal grouponPrice = new BigDecimal(0.00);
        LitemallGrouponRules grouponRules = grouponRulesService.findById(grouponRulesId);
        if (grouponRules != null) {
            grouponPrice = grouponRules.getDiscount();
        }

        // 待结算商品
        List<LitemallCart> checkedGoodsList;
        if (cartId == null || cartId.equals(0)) {
            checkedGoodsList = cartService.queryByUidAndChecked(userId);
        } else {
            LitemallCart cart = cartService.findById(userId, cartId);
            if (cart == null) {
                return ResponseUtil.badArgumentValue();
            }
            checkedGoodsList = new ArrayList<>(1);
            checkedGoodsList.add(cart);
        }
        BigDecimal checkedGoodsPrice = calcCheckedGoodsPrice(checkedGoodsList, grouponRules, grouponPrice);

        // 优惠券：不使用 / 自动挑选 / 校验用户所选
        CouponChoice couponChoice = chooseCoupon(userId, couponId, userCouponId, checkedGoodsPrice, checkedGoodsList);

        // 运费，满额免运费
        BigDecimal freightPrice = new BigDecimal(0.00);
        if (checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            freightPrice = SystemConfig.getFreight();
        }
        // 可以使用的其他钱，例如用户积分
        BigDecimal integralPrice = new BigDecimal(0.00);
        BigDecimal orderTotalPrice = checkedGoodsPrice.add(freightPrice)
                .subtract(couponChoice.couponPrice).max(new BigDecimal(0.00));
        BigDecimal actualPrice = orderTotalPrice.subtract(integralPrice);

        Map<String, Object> data = new HashMap<>();
        data.put("addressId", addressId);
        data.put("couponId", couponChoice.couponId);
        data.put("userCouponId", couponChoice.userCouponId);
        data.put("cartId", cartId);
        data.put("grouponRulesId", grouponRulesId);
        data.put("grouponPrice", grouponPrice);
        data.put("checkedAddress", checkedAddress);
        data.put("availableCouponLength", couponChoice.availableCouponLength);
        data.put("goodsTotalPrice", checkedGoodsPrice);
        data.put("freightPrice", freightPrice);
        data.put("couponPrice", couponChoice.couponPrice);
        data.put("orderTotalPrice", orderTotalPrice);
        data.put("actualPrice", actualPrice);
        data.put("checkedGoodsList", checkedGoodsList);
        return ResponseUtil.ok(data);
    }

    private boolean hasRequiredCartFields(LitemallCart cart) {
        Integer productId = cart.getProductId();
        Integer goodsId = cart.getGoodsId();
        Short number = cart.getNumber();
        return ObjectUtils.allNotNull(productId, goodsId, number) && number > 0;
    }

    /**
     * 新增购物车项，并校验规格库存。
     *
     * @return 失败响应；成功返回 null
     */
    private Object addNewCartItem(Integer userId, LitemallCart cart, LitemallGoods goods, LitemallGoodsProduct product) {
        int number = cart.getNumber().intValue();
        if (product == null || number > product.getNumber()) {
            return ResponseUtil.fail(GOODS_NO_STOCK, "库存不足");
        }
        cart.setId(null);
        cart.setGoodsSn(goods.getGoodsSn());
        cart.setGoodsName(goods.getName());
        cart.setPicUrl(StringUtils.isEmpty(product.getUrl()) ? goods.getPicUrl() : product.getUrl());
        cart.setPrice(product.getPrice());
        cart.setSpecifications(product.getSpecifications());
        cart.setUserId(userId);
        cart.setChecked(true);
        cartService.add(cart);
        return null;
    }

    /**
     * add 场景：已存在购物车项时累加数量。
     */
    private Object increaseCartNumber(LitemallCart existCart, Short number, LitemallGoodsProduct product) {
        int num = existCart.getNumber() + number;
        if (product == null || num > product.getNumber()) {
            return ResponseUtil.fail(GOODS_NO_STOCK, "库存不足");
        }
        existCart.setNumber((short) num);
        if (cartService.updateById(existCart) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return null;
    }

    /**
     * fastadd 场景：已存在购物车项时覆盖数量。
     */
    private Object overwriteCartNumber(LitemallCart existCart, Short number, LitemallGoodsProduct product) {
        int num = number;
        if (product == null || num > product.getNumber()) {
            return ResponseUtil.fail(GOODS_NO_STOCK, "库存不足");
        }
        existCart.setNumber((short) num);
        if (cartService.updateById(existCart) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return null;
    }

    private LitemallAddress resolveAddress(Integer userId, Integer addressId) {
        if (addressId != null && !addressId.equals(0)) {
            LitemallAddress address = addressService.query(userId, addressId);
            if (address != null) {
                return address;
            }
        }
        LitemallAddress defaultAddress = addressService.findDefault(userId);
        if (defaultAddress != null) {
            return defaultAddress;
        }
        // 没有收货地址时返回 id=0 的空地址，前端据此提醒添加地址
        LitemallAddress emptyAddress = new LitemallAddress();
        emptyAddress.setId(0);
        return emptyAddress;
    }

    private BigDecimal calcCheckedGoodsPrice(List<LitemallCart> goodsList, LitemallGrouponRules grouponRules,
                                             BigDecimal grouponPrice) {
        BigDecimal total = new BigDecimal(0.00);
        for (LitemallCart cart : goodsList) {
            BigDecimal unitPrice = cart.getPrice();
            if (grouponRules != null && grouponRules.getGoodsId().equals(cart.getGoodsId())) {
                unitPrice = unitPrice.subtract(grouponPrice);
            }
            total = total.add(unitPrice.multiply(new BigDecimal(cart.getNumber())));
        }
        return total;
    }

    /**
     * 三种情况：不使用优惠券（-1）/ 自动挑选（0）/ 校验用户所选。
     */
    private CouponChoice chooseCoupon(Integer userId, Integer couponId, Integer userCouponId,
                                      BigDecimal checkedGoodsPrice, List<LitemallCart> checkedGoodsList) {
        CouponChoice best = findBestCoupon(userId, checkedGoodsPrice, checkedGoodsList);
        if (couponId == null || couponId.equals(-1)) {
            CouponChoice none = new CouponChoice();
            none.couponId = -1;
            none.userCouponId = -1;
            none.couponPrice = new BigDecimal(0);
            none.availableCouponLength = best.availableCouponLength;
            return none;
        }
        if (couponId.equals(0)) {
            return best;
        }
        LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponId, userCouponId,
                checkedGoodsPrice, checkedGoodsList);
        if (coupon == null) {
            // 用户选择的优惠券不可用，退化为自动挑选
            return best;
        }
        CouponChoice chosen = new CouponChoice();
        chosen.couponId = couponId;
        chosen.userCouponId = userCouponId;
        chosen.couponPrice = coupon.getDiscount();
        chosen.availableCouponLength = best.availableCouponLength;
        return chosen;
    }

    /**
     * 在用户持有的可用优惠券中挑出减免金额最大的一张。
     */
    private CouponChoice findBestCoupon(Integer userId, BigDecimal checkedGoodsPrice,
                                        List<LitemallCart> checkedGoodsList) {
        CouponChoice best = new CouponChoice();
        best.couponPrice = new BigDecimal(0.00);
        best.couponId = 0;
        best.userCouponId = 0;
        for (LitemallCouponUser couponUser : couponUserService.queryAll(userId)) {
            LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponUser.getCouponId(),
                    couponUser.getId(), checkedGoodsPrice, checkedGoodsList);
            if (coupon == null) {
                continue;
            }
            best.availableCouponLength++;
            if (best.couponPrice.compareTo(coupon.getDiscount()) < 0) {
                best.couponPrice = coupon.getDiscount();
                best.couponId = coupon.getId();
                best.userCouponId = couponUser.getId();
            }
        }
        return best;
    }

    /**
     * 结算页的优惠券选择结果。
     */
    private static final class CouponChoice {
        private Integer couponId;
        private Integer userCouponId;
        private BigDecimal couponPrice;
        private int availableCouponLength;
    }
}
