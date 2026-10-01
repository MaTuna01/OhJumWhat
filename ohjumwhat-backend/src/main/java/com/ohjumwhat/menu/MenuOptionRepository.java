package com.ohjumwhat.menu;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuOptionRepository extends JpaRepository<MenuOption, Long> {

	List<MenuOption> findByPollIdOrderByIdAsc(Long pollId);

	List<MenuOption> findByPollIdIn(Collection<Long> pollIds);

	boolean existsByPollIdAndName(Long pollId, String name);

	long countByPollId(Long pollId);

	/** 메뉴 자동완성: 같은 조직의 과거 투표에 올라온 메뉴 이름을 최근 순으로 중복 없이 */
	@Query("""
			select m.name from MenuOption m join com.ohjumwhat.poll.Poll p on p.id = m.pollId
			where p.organizationId = :organizationId and lower(m.name) like lower(concat('%', :query, '%'))
			group by m.name
			order by max(m.createdAt) desc""")
	List<String> findRecentNames(Long organizationId, String query, Pageable pageable);
}
