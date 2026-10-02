package com.ohjumwhat.menu;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuCommentRepository extends JpaRepository<MenuComment, Long> {

	/** 메뉴의 댓글(오래된 순). 이름은 별명(없으면 구글 이름), 사진은 올린 사진(없으면 구글 사진) */
	@Query("""
			select new com.ohjumwhat.menu.MenuCommentRow(c.id, u.id, coalesce(u.nickname, u.name), u.photoKey,
				u.profileImageUrl, c.body, c.createdAt, c.editedAt)
			from MenuComment c left join com.ohjumwhat.user.User u on u.id = c.userId
			where c.optionId = :optionId
			order by c.id""")
	List<MenuCommentRow> findRows(Long optionId);

	/** 메뉴별 댓글 수(댓글이 없는 메뉴는 빠진다) */
	@Query("""
			select new com.ohjumwhat.menu.MenuCommentCount(c.optionId, count(c))
			from MenuComment c
			where c.optionId in :optionIds
			group by c.optionId""")
	List<MenuCommentCount> countByOptionIds(Collection<Long> optionIds);
}
