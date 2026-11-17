package com.moava.group.create.service;

import com.moava.group.create.dto.GroupInviteResponse;
import com.moava.group.create.mapper.GroupInviteMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInviteService {

    private final GroupInviteMapper groupInviteMapper;

    public GroupInviteService(GroupInviteMapper groupInviteMapper) {
        this.groupInviteMapper = groupInviteMapper;
    }

    @Transactional(readOnly = true)
    public GroupInviteResponse getInvite(String token) {

        // 32자리 16진수 초대 토큰 형식 검증
        if (token == null
                || !token.matches("[0-9a-fA-F]{32}")) {
            return null;
        }

        return groupInviteMapper.findByInviteCode(token);
    }
}