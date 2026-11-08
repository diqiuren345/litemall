package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CouponVerifyService {

    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallGoodsService goodsService;

    /**
     * 检测优惠券是否适合
     *
     * @param userId
     * @param couponId
     * @param checkedGoodsPrice
     * @return
     */
    public LitemallCoupon checkCoupon(Integer userId, Integer couponId, Integer userCouponId,
                                      BigDecimal checkedGoodsPrice, List<LitemallCart> cartList) {
        LitemallCoupon coupon = couponService.findById(couponId);
        if (coupon == null || coupon.getDeleted()) {
            return null;
        }

        LitemallCouponUser couponUser = resolveCouponUser(userId, couponId, userCouponId);
        if (couponUser == null) {
            return null;
        }
        if (!isInValidTime(coupon, couponUser)) {
            return null;
        }
        if (!isMatchGoods(coupon, cartList)) {
            return null;
        }
        if (!CouponConstant.STATUS_NORMAL.equals(coupon.getStatus())) {
            return null;
        }
        // 检测是否满足最低消费
        if (checkedGoodsPrice.compareTo(coupon.getMin()) < 0) {
            return null;
        }
        return coupon;
    }

    /**
     * 定位该用户持有的优惠券记录：优先按 userCouponId，找不到再按 (userId, couponId) 查。
     *
     * @return 优惠券记录；记录与 couponId 不匹配时返回 null
     */
    private LitemallCouponUser resolveCouponUser(Integer userId, Integer couponId, Integer userCouponId) {
        LitemallCouponUser couponUser = couponUserService.findById(userCouponId);
        if (couponUser == null) {
            return couponUserService.queryOne(userId, couponId);
        }
        if (!couponId.equals(couponUser.getCouponId())) {
            return null;
        }
        return couponUser;
    }

    /**
     * 按时间类型判断优惠券是否在可用期内。
     */
    private boolean isInValidTime(LitemallCoupon coupon, LitemallCouponUser couponUser) {
        Short timeType = coupon.getTimeType();
        LocalDateTime now = LocalDateTime.now();
        if (CouponConstant.TIME_TYPE_TIME.equals(timeType)) {
            return !now.isBefore(coupon.getStartTime()) && !now.isAfter(coupon.getEndTime());
        }
        if (CouponConstant.TIME_TYPE_DAYS.equals(timeType)) {
            LocalDateTime expired = couponUser.getAddTime().plusDays(coupon.getDays());
            return !now.isAfter(expired);
        }
        return false;
    }

    /**
     * 指定商品/分类的优惠券，需要校验购物车中命中商品的总价是否达到门槛；
     * 其他类型优惠券不做商品维度过滤。
     */
    private boolean isMatchGoods(LitemallCoupon coupon, List<LitemallCart> cartList) {
        Short goodType = coupon.getGoodsType();
        boolean limited = CouponConstant.GOODS_TYPE_CATEGORY.equals(goodType)
                || CouponConstant.GOODS_TYPE_ARRAY.equals(goodType);
        if (!limited) {
            return true;
        }
        Map<Integer, List<LitemallCart>> cartMap = groupCart(goodType, cartList);
        List<Integer> goodsValueList = new ArrayList<>(Arrays.asList(coupon.getGoodsValue()));
        goodsValueList.retainAll(cartMap.keySet());
        if (goodsValueList.isEmpty()) {
            return false;
        }
        BigDecimal total = new BigDecimal(0);
        for (Integer key : goodsValueList) {
            for (LitemallCart cart : cartMap.get(key)) {
                total = total.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }
        return total.compareTo(coupon.getMin()) >= 0;
    }

    private Map<Integer, List<LitemallCart>> groupCart(Short goodType, List<LitemallCart> cartList) {
        boolean byGoods = CouponConstant.GOODS_TYPE_ARRAY.equals(goodType);
        Map<Integer, List<LitemallCart>> cartMap = new HashMap<>();
        for (LitemallCart cart : cartList) {
            Integer key = byGoods ? cart.getGoodsId()
                    : goodsService.findById(cart.getGoodsId()).getCategoryId();
            if (!cartMap.containsKey(key)) {
                cartMap.put(key, new LinkedList<LitemallCart>());
            }
            cartMap.get(key).add(cart);
        }
        return cartMap;
    }

}