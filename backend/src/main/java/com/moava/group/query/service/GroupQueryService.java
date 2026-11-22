package com.moava.group.query.service;

import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.mapper.GroupQueryMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class GroupQueryService {

    private final GroupQueryMapper groupQueryMapper;

    public GroupQueryService(GroupQueryMapper groupQueryMapper) {
        this.groupQueryMapper = groupQueryMapper;
    }

    @Transactional(readOnly = true)
    public List<GroupListResponse> getMyGroups(Long memberId) {

        // 내가 모임장인 모임
        List<GroupListResponse> groups = new ArrayList<>(
                groupQueryMapper.findMyOwnedGroups(memberId)
        );

        // 내가 일반 회원으로 참여 중인 모임
        groups.addAll(
                groupQueryMapper.findMyJoinedGroups(memberId)
        );

        // 모임 ID 내림차순 정렬
        groups.sort(
                Comparator.comparing(
                        GroupListResponse::groupId
                ).reversed()
        );

        return groups;
    }
}
