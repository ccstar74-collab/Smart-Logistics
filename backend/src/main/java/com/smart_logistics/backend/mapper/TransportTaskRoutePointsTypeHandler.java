package com.smart_logistics.backend.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TransportTaskRoutePointsTypeHandler
        extends BaseTypeHandler<List<List<Double>>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<List<Double>>> POINTS_TYPE =
            new TypeReference<>() {
            };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    List<List<Double>> parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, serialize(parameter));
    }

    @Override
    public List<List<Double>> getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public List<List<Double>> getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public List<List<Double>> getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return deserialize(statement.getString(columnIndex));
    }

    public String serialize(List<List<Double>> points) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(copyAndValidate(points));
        } catch (JsonProcessingException exception) {
            throw new SQLException("failed to serialize transport task route points", exception);
        }
    }

    public List<List<Double>> deserialize(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return copyAndValidate(OBJECT_MAPPER.readValue(json, POINTS_TYPE));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SQLException("failed to deserialize transport task route points", exception);
        }
    }

    private List<List<Double>> copyAndValidate(List<List<Double>> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException(
                    "transport task route must contain at least two points");
        }
        return points.stream().map(point -> {
            if (point == null || point.size() != 2
                    || point.get(0) == null || point.get(1) == null) {
                throw new IllegalArgumentException(
                        "transport task route point must contain longitude and latitude");
            }
            double longitude = point.get(0);
            double latitude = point.get(1);
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                    || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException(
                        "transport task route point is outside the valid range");
            }
            return List.copyOf(point);
        }).toList();
    }
}
