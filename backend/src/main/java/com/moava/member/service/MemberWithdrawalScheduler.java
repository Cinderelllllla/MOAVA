package com.moava.member.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.moava.member.mapper.MemberMapper;
import com.moava.member.mapper.SocialAccountMapper;

@Component
public class MemberWithdrawalScheduler{
    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawalScheduler.class);

    private final MemberMapper memberMapper;
    private final SocialAccountMapper socialAccountMapper;

    @Autowired
    public MemberWithdrawalScheduler(MemberMapper memberMapper, SocialAccountMapper socialAccountMapper){
        this.memberMapper = memberMapper;
        this.socialAccountMapper = socialAccountMapper;
    }

    public MemberWithdrawalScheduler(MemberMapper memberMapper){
        this(memberMapper, null);
    }

    @Scheduled(cron = " 0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteExpireMember(){
        List<Long> memberIds = memberMapper.findExpiredWithdrawnMember();

        for(Long memberId : memberIds){
            try{
                int result = memberMapper.deleteWithdrawnMember(memberId);

                if(result == 1){
                    memberMapper.rejectPendingJoinRequests(memberId);
                    if(socialAccountMapper != null){
                        socialAccountMapper.deleteAllSocialAccounts(memberId);
                    }
                    log.info("탈퇴 회원 개인정보 파기 완료 : {}", memberId);
                }
            }catch(Exception e){
                log.error("탈퇴 회원 삭제 실패 : {}", memberId, e);
            }
        }
    }
}
