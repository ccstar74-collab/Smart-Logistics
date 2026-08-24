package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.response.OwnerOptionResponse;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OwnerService {

    private final OwnerMapper ownerMapper;
    private final UserMapper userMapper;

    public OwnerService(OwnerMapper ownerMapper, UserMapper userMapper) {
        this.ownerMapper = ownerMapper;
        this.userMapper = userMapper;
    }

    public List<OwnerOptionResponse> listOptions() {
        List<Owner> owners = ownerMapper.selectList(
                new LambdaQueryWrapper<Owner>().orderByAsc(Owner::getId));
        if (owners.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(owners.stream()
                        .map(Owner::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return owners.stream()
                .filter(owner -> isActiveOwner(users.get(owner.getUserId())))
                .map(owner -> {
                    User user = users.get(owner.getUserId());
                    return new OwnerOptionResponse(owner.getId(), user.getId(),
                            displayName(user, owner), owner.getCompanyName());
                })
                .toList();
    }

    private boolean isActiveOwner(User user) {
        return user != null
                && UserRole.OWNER.name().equals(user.getRole())
                && UserStatus.ACTIVE.name().equals(user.getStatus());
    }

    private String displayName(User user, Owner owner) {
        if (StringUtils.hasText(user.getName())) {
            return user.getName();
        }
        if (StringUtils.hasText(owner.getContactPerson())) {
            return owner.getContactPerson();
        }
        return user.getUsername();
    }
}
