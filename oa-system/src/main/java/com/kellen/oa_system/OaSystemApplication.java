package com.kellen.oa_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用入口。
 *
 * <p>开启定时任务能力：目前用于公告的定时发布与自动下架
 * （见 {@code com.kellen.oa_system.notice.task.NoticeScheduleTask}）。</p>
 */
@EnableScheduling
@SpringBootApplication
public class OaSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(OaSystemApplication.class, args);
	}

}
