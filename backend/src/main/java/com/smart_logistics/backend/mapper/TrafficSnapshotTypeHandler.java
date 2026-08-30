package com.smart_logistics.backend.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TrafficSnapshotTypeHandler extends BaseTypeHandler<TrafficSnapshot> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    TrafficSnapshot parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, serialize(parameter));
    }

    @Override
    public TrafficSnapshot getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public TrafficSnapshot getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public TrafficSnapshot getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return deserialize(statement.getString(columnIndex));
    }

    public String serialize(TrafficSnapshot snapshot) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new SQLException("failed to serialize route traffic snapshot", exception);
        }
    }

    public TrafficSnapshot deserialize(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, TrafficSnapshot.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SQLException("failed to deserialize route traffic snapshot", exception);
        }
    }
}
