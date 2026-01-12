package com.cyan.cdc.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源mapper orm对象
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper
public interface CdcConfigMapper extends BaseMapper<CdcConfigDO> {
}
