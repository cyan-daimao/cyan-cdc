package com.cyan.cdc.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 数据源mapper orm对象
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper
public interface CdcConfigMapper extends BaseMapper<CdcConfigDO> {

    @Select("""
            SELECT
                IFNULL(MAX(server_id) + 1, 30000) AS new_server_id
            FROM cdc_config
            """)
    int findAServerId();
}
