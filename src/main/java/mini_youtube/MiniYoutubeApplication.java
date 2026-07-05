package mini_youtube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MiniYoutubeApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniYoutubeApplication.class, args);
	}

}
