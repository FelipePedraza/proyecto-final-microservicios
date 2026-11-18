package co.edu.uniquindio.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import observability.SecretLoader;
import observability.VaultSecretLoader;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		if (!VaultSecretLoader.loadIfConfigured()) {
			SecretLoader.load("db_auth_password", "DB_PASSWORD");
			SecretLoader.load("broker_password", "RABBITMQ_PASSWORD");
			SecretLoader.load("jwt_secret", "JWT_SECRET");
			SecretLoader.load("auth_admin_password", "ADMIN_PASSWORD");
		}
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
