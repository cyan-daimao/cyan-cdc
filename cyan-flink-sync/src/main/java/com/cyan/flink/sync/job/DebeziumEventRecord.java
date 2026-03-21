package com.cyan.flink.sync.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 顶层容器对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class DebeziumEventRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Schema schema;
    private Payload payload;


    public Field[] getFields() {
        for (Field field : this.schema.fields) {
            String name = this.schema.name.substring(0, this.schema.name.lastIndexOf("."));
            if (field.getName().contains(name) && "after".equals(field.getField())){
                return field.getFields();
            }
        }
        return null;
    }


    /**
     * Schema 结构体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Schema implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String type;
        private Field[] fields;
        private boolean optional;
        private String name;
        private int version;
    }

    /**
     * 字段定义结构体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Field implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String type;
        private boolean optional;
        private String field;
        private Field[] fields;
        private String name;
        private int version;
        private String defaultValue; // 对应 JSON 中的 default
        private Parameters parameters;
    }

    /**
     * 参数结构体（仅用于 snapshot 字段的枚举配置）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Parameters implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String allowed;
    }

    /**
     * 载荷结构体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Payload implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private Map<String,Object> before;
        private Map<String,Object> after;
        private Source source;
        private Transaction transaction;
        private String op;
        private Long ts_ms;
        private Long ts_us;
        private Long ts_ns;
    }


    /**
     * 数据源信息结构体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Source implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String version;
        private String connector;
        private String name;
        private Long ts_ms;
        private String snapshot;
        private String db;
        private String sequence;
        private Long ts_us;
        private Long ts_ns;
        private String table;
        private Long server_id;
        private String gtid;
        private String file;
        private Long pos;
        private Integer row;
        private Long thread;
        private String query;
    }

    /**
     * 事务信息结构体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Transaction implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private String id;
        private Long total_order;
        private Long data_collection_order;
    }
}