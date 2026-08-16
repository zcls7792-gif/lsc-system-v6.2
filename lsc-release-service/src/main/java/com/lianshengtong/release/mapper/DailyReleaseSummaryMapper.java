package com.lianshengtong.release.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日释放汇总 Mapper
 */
@Mapper
public interface DailyReleaseSummaryMapper extends BaseMapper<DailyReleaseSummary> {

    /**
     * 按日期查询当日释放汇总(断点续跑依据)
     */
    default DailyReleaseSummary findByDate(LocalDate date) {
        return selectOne(new LambdaQueryWrapper<DailyReleaseSummary>()
                .eq(DailyReleaseSummary::getDate, date));
    }

    /**
     * 查询最近一次未成功/未对账的汇总(用于次日任务阻断判断)
     */
    @Select({"<script>",
            "SELECT * FROM daily_release_summary",
            "WHERE status IN",
            "<foreach collection='statusList' item='s' open='(' separator=',' close=')'>#{s}</foreach>",
            "ORDER BY `date` DESC LIMIT 1",
            "</script>"})
    DailyReleaseSummary selectLastUnreconciled(@Param("statusList") List<Integer> statusList);
}
