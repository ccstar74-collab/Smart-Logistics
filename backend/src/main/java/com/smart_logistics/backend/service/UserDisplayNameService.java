package com.smart_logistics.backend.service;

import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserDisplayNameService {

    private final DriverMapper driverMapper;
    private final OwnerMapper ownerMapper;
    private final UserMapper userMapper;

    public UserDisplayNameService(DriverMapper driverMapper, OwnerMapper ownerMapper,
                                  UserMapper userMapper) {
        this.driverMapper = driverMapper;
        this.ownerMapper = ownerMapper;
        this.userMapper = userMapper;
    }

    public Map<Long, String> getDriverNames(Collection<Long> driverIds) {
        List<Long> ids = distinctIds(driverIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Driver> drivers = driverMapper.selectBatchIds(ids);
        Map<Long, User> users = usersById(drivers.stream().map(Driver::getUserId).toList());
        Map<Long, String> names = new HashMap<>();
        for (Driver driver : drivers) {
            User user = users.get(driver.getUserId());
            if (user != null) {
                names.put(driver.getId(), userName(user));
            }
        }
        return names;
    }

    public Map<Long, String> getOwnerNames(Collection<Long> ownerIds) {
        List<Long> ids = distinctIds(ownerIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Owner> owners = ownerMapper.selectBatchIds(ids);
        Map<Long, User> users = usersById(owners.stream().map(Owner::getUserId).toList());
        Map<Long, String> names = new HashMap<>();
        for (Owner owner : owners) {
            User user = users.get(owner.getUserId());
            if (user != null) {
                names.put(owner.getId(), ownerName(owner, user));
            }
        }
        return names;
    }

    private Map<Long, User> usersById(Collection<Long> userIds) {
        List<Long> ids = distinctIds(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String userName(User user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername();
    }

    private String ownerName(Owner owner, User user) {
        if (StringUtils.hasText(user.getName())) {
            return user.getName();
        }
        if (StringUtils.hasText(owner.getContactPerson())) {
            return owner.getContactPerson();
        }
        return user.getUsername();
    }
}
