package com.zcls.lsc.account.ledger;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.common.money.Units;

import java.util.Objects;

/**
 * 单条分录值对象（6.1）。
 * bucket 标识余额桶，delta 为有符号变化量（负向用负数）。
 * grant_lot_id / available_lot_id 关联具体批次，用于重建逐批次变化。
 * allocation_ref 关联 reservation/consumption/freeze/recovery。
 * 零余额变化的余数更新不写分录，保存在事件 payload。
 */
public final class BucketDelta {

    private final Bucket bucket;
    private final long deltaUnit;
    private final Long grantLotId;
    private final Long availableLotId;
    private final DispositionType dispositionType;
    private final String allocationRef;

    public BucketDelta(Bucket bucket, long deltaUnit, DispositionType dispositionType) {
        this(bucket, deltaUnit, dispositionType, null, null, null);
    }

    public BucketDelta(Bucket bucket, long deltaUnit, DispositionType dispositionType,
                       Long grantLotId, Long availableLotId, String allocationRef) {
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.deltaUnit = deltaUnit;
        this.dispositionType = Objects.requireNonNull(dispositionType, "dispositionType");
        this.grantLotId = grantLotId;
        this.availableLotId = availableLotId;
        this.allocationRef = allocationRef;
    }

    public static BucketDelta of(Bucket bucket, Units delta, DispositionType dispositionType) {
        return new BucketDelta(bucket, delta.longValue(), dispositionType);
    }

    public Bucket bucket() {
        return bucket;
    }

    public long deltaUnit() {
        return deltaUnit;
    }

    public Long grantLotId() {
        return grantLotId;
    }

    public Long availableLotId() {
        return availableLotId;
    }

    public DispositionType dispositionType() {
        return dispositionType;
    }

    public String allocationRef() {
        return allocationRef;
    }

    public boolean isZero() {
        return deltaUnit == 0L;
    }

    @Override
    public String toString() {
        return "BucketDelta{" + bucket + " " + (deltaUnit >= 0 ? "+" : "") + deltaUnit
                + " unit, disp=" + dispositionType + "}";
    }
}
