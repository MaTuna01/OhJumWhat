package com.ohjumwhat.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.ohjumwhat.user.User;

/** 관리자 콘솔 조회 전용 쿼리. 여러 엔티티를 가로지르므로 한곳에 모아 둔다. 검색어 q는 빈 문자열이면 전체 */
interface AdminRepository extends Repository<User, Long> {

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$UserRow(u.id, u.name, u.email, u.profileImageUrl, u.role,
				u.createdAt, u.lastLoginAt,
				(select count(m) from com.ohjumwhat.organization.Membership m where m.userId = u.id))
			from User u
			where :q = '' or lower(u.name) like lower(concat('%', :q, '%'))
			   or lower(u.email) like lower(concat('%', :q, '%'))
			order by u.createdAt desc, u.id desc""")
	List<AdminResponses.UserRow> findUsers(String q, Pageable pageable);

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$UserRow(u.id, u.name, u.email, u.profileImageUrl, u.role,
				u.createdAt, u.lastLoginAt,
				(select count(m) from com.ohjumwhat.organization.Membership m where m.userId = u.id))
			from User u where u.id = :id""")
	Optional<AdminResponses.UserRow> findUser(Long id);

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$UserOrganization(o.id, o.name,
				(select count(m2) from com.ohjumwhat.organization.Membership m2 where m2.organizationId = o.id),
				m.joinedAt, m.lastVisitedAt)
			from com.ohjumwhat.organization.Membership m
			join com.ohjumwhat.organization.Organization o on o.id = m.organizationId
			where m.userId = :userId
			order by m.joinedAt, m.id""")
	List<AdminResponses.UserOrganization> findOrganizationsOfUser(Long userId);

	@Query("select count(p) from com.ohjumwhat.poll.Poll p where p.createdBy = :userId")
	long countPollsCreatedBy(Long userId);

	@Query("select count(o) from com.ohjumwhat.menu.MenuOption o where o.createdBy = :userId")
	long countMenusAddedBy(Long userId);

	@Query("select count(v) from com.ohjumwhat.vote.Vote v where v.userId = :userId")
	long countResponsesBy(Long userId);

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$OrganizationRow(o.id, o.name, o.createdAt,
				(select count(m) from com.ohjumwhat.organization.Membership m where m.organizationId = o.id),
				(select count(p) from com.ohjumwhat.poll.Poll p where p.organizationId = o.id),
				(select max(p2.pollDate) from com.ohjumwhat.poll.Poll p2 where p2.organizationId = o.id))
			from com.ohjumwhat.organization.Organization o
			where :q = '' or lower(o.name) like lower(concat('%', :q, '%'))
			order by o.createdAt desc, o.id desc""")
	List<AdminResponses.OrganizationRow> findOrganizations(String q, Pageable pageable);

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$OrganizationRow(o.id, o.name, o.createdAt,
				(select count(m) from com.ohjumwhat.organization.Membership m where m.organizationId = o.id),
				(select count(p) from com.ohjumwhat.poll.Poll p where p.organizationId = o.id),
				(select max(p2.pollDate) from com.ohjumwhat.poll.Poll p2 where p2.organizationId = o.id))
			from com.ohjumwhat.organization.Organization o where o.id = :id""")
	Optional<AdminResponses.OrganizationRow> findOrganization(Long id);

	/** 조직 멤버(가입 순). 맨 위가 가장 먼저 들어온 사람(대개 만든 사람)이다. */
	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$Member(u.id, u.name, u.email, u.profileImageUrl, u.role,
				m.joinedAt, m.lastVisitedAt)
			from com.ohjumwhat.organization.Membership m join User u on u.id = m.userId
			where m.organizationId = :organizationId
			order by m.joinedAt, m.id""")
	List<AdminResponses.Member> findMembers(Long organizationId);

	/** 조직의 최근 투표: [id, title, pollDate, opensAt, closesAt, scheduleId, 메뉴 수, 응답 수] */
	@Query("""
			select p.id, p.title, p.pollDate, p.opensAt, p.closesAt, p.scheduleId,
				(select count(o) from com.ohjumwhat.menu.MenuOption o where o.pollId = p.id),
				(select count(v) from com.ohjumwhat.vote.Vote v where v.pollId = p.id)
			from com.ohjumwhat.poll.Poll p
			where p.organizationId = :organizationId
			order by p.pollDate desc, p.opensAt desc, p.id desc""")
	List<Object[]> findRecentPolls(Long organizationId, Pageable pageable);

	@Query("""
			select new com.ohjumwhat.admin.AdminResponses$Block(b.id, b.email, b.name, b.blockedAt, u.name)
			from com.ohjumwhat.user.BlockedAccount b left join User u on u.id = b.blockedBy
			order by b.blockedAt desc, b.id desc""")
	List<AdminResponses.Block> findBlocks();

	@Query("select count(u) from User u")
	long countUsers();

	@Query("select count(u) from User u where u.createdAt >= :since")
	long countUsersSince(Instant since);

	@Query("select count(o) from com.ohjumwhat.organization.Organization o")
	long countOrganizations();

	@Query("select count(p) from com.ohjumwhat.poll.Poll p where p.pollDate = :today")
	long countPollsOn(LocalDate today);

	@Query("select count(p) from com.ohjumwhat.poll.Poll p where p.opensAt <= :now and p.closesAt > :now")
	long countOpenPolls(Instant now);

	@Query("select count(b) from com.ohjumwhat.user.BlockedAccount b")
	long countBlocks();
}
