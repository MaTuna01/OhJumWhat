package com.ohjumwhat.organization;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

	Optional<Membership> findFirstByUserIdOrderByLastVisitedAtDesc(Long userId);

	Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId);

	boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

	long countByOrganizationId(Long organizationId);

	/** 내가 속한 조직 목록(가입 순). hasOpenPollToday는 서비스에서 채운다. */
	@Query("""
			select new com.ohjumwhat.organization.MyOrganizationRow(
				o.id, o.name, (select count(m2) from Membership m2 where m2.organizationId = o.id))
			from Membership m join Organization o on o.id = m.organizationId
			where m.userId = :userId
			order by m.joinedAt, m.id""")
	List<MyOrganizationRow> findMyOrganizations(Long userId);

	@Query("""
			select new com.ohjumwhat.organization.MemberResponse(u.id, coalesce(u.nickname, u.name), u.profileImageUrl,
				m.joinedAt)
			from Membership m join com.ohjumwhat.user.User u on u.id = m.userId
			where m.organizationId = :organizationId
			order by m.joinedAt, m.id""")
	List<MemberResponse> findMembers(Long organizationId);

	/** 강제 탈퇴: 그 회원이 속한 조직 ID(잠금 순서를 맞추기 위해 오름차순) */
	@Query("select m.organizationId from Membership m where m.userId = :userId order by m.organizationId")
	List<Long> findOrganizationIdsByUserId(Long userId);
}
