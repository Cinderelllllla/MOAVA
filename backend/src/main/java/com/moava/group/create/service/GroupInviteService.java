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

    // [F18 추가] 모임장 전용 초대 토큰 조회
    @Transactional(readOnly = true)
    public String getInviteCodeForOwner(
            Long groupId,
            Long memberId
    ) {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException(
                    "올바르지 않은 모임 ID입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        String inviteCode =
                groupInviteMapper.findInviteCodeForOwner(
                        groupId,
                        memberId
                );

        if (inviteCode == null) {
            throw new SecurityException(
                    "초대 링크를 조회할 권한이 없거나 조회할 수 없는 모임입니다."
            );
        }

        return inviteCode;
    }
}