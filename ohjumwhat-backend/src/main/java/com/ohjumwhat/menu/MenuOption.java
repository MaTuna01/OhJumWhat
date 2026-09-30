package com.ohjumwhat.menu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_options")
public class MenuOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long pollId;

	@Column(nullable = false)
	private Long createdBy;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private Instant createdAt;

	protected MenuOption() {
	}

	public MenuOption(Long pollId, Long createdBy, String name) {
		this.pollId = pollId;
		this.createdBy = createdBy;
		this.name = name;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getPollId() {
		return pollId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
