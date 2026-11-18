package com.moava.group.query.service;

import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.mapper.GroupQueryMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupQueryService {

    private final GroupQueryMapper groupQueryMapper;

    public GroupQueryService(GroupQueryMapper groupQueryMapper) {
        this.groupQueryMapper = groupQueryMapper;
    }

    @Transactional(readOnly = true)
    public List<GroupListResponse> getMyGroups(Long memberId) {
        return groupQueryMapper.findMyOwnedGroups(memberId);
    }
}