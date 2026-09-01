package com.smart_logistics.backend.contract;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.smart_logistics.backend.entity.InitialRouteCandidate;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InitialRouteCandidateMappingTest {

    @Test
    void rankColumnDoesNotGenerateReservedWordAlias() {
        TableInfo tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(),
                        "initial-route-candidate-mapping-test"),
                InitialRouteCandidate.class);
        TableFieldInfo rankField = tableInfo.getFieldList().stream()
                .filter(field -> field.getProperty().equals("rankNo"))
                .findFirst()
                .orElseThrow();

        assertEquals("rank_no", rankField.getColumn());
        assertFalse(rankField.getSqlSelect().toLowerCase().contains(" as rank"));
    }
}
