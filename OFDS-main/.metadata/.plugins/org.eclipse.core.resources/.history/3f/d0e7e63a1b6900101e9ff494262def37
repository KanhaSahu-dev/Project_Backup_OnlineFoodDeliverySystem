package com.ofds.apigateway.filters;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class CentralizedAuthFilter extends AbstractGatewayFilterFactory<CentralizedAuthFilter.Config> {

	public CentralizedAuthFilter() {
		super(Config.class);
	}
	
	public static class Config {
		private String requiredRole; // e.g., "CUSTOMER", "ADMIN"
		private boolean matchUserIdToPathId; // true if userId from JWT must match path variable ID

		public String getRequiredRole() {
			return requiredRole;
		}

		public void setRequiredRole(String requiredRole) {
			this.requiredRole = requiredRole;
		}

		public boolean isMatchUserIdToPathId() {
			return matchUserIdToPathId;
		}

		public void setMatchUserIdToPathId(boolean matchUserIdToPathId) {
			this.matchUserIdToPathId = matchUserIdToPathId;
		}
	}



	@Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Spring Security ensures JwtAuthenticationToken exists if authenticated() was successful
            return exchange.getPrincipal()
                    .filter(principal -> principal instanceof JwtAuthenticationToken)
                    .cast(JwtAuthenticationToken.class)
                    .flatMap(jwtAuth -> {
                        // Get JWT claims
                        Map<String, Object> claims = jwtAuth.getToken().getClaims();

                        // --- 1. Role Check ---
                        if (config.getRequiredRole() != null && !config.getRequiredRole().isEmpty()) {
                            Object rolesClaim = claims.get("roles"); // Assuming "roles" claim in JWT
                            boolean hasRequiredRole = false;
                            if (rolesClaim instanceof String) {
                                // Handle comma-separated roles
                                hasRequiredRole = List.of(((String) rolesClaim).replaceAll("[//][//]","") .split(","))
                                        .stream()
                                        .anyMatch(role -> role.trim().equalsIgnoreCase(config.getRequiredRole()));
                            } else if (rolesClaim instanceof List) {
                                // Handle roles as a list
                                hasRequiredRole = ((List<?>) rolesClaim).stream()
                                        .map(Object::toString)
                                        .anyMatch(role -> role.trim().equalsIgnoreCase(config.getRequiredRole()));
                            }

                            if (!hasRequiredRole) {
                                return denyAccess(exchange, "Access Denied: Missing required role.");
                            }
                        }

                        // --- 2. User ID to Path ID Matching ---
                        if (config.isMatchUserIdToPathId()) {
                            // Extract path ID using a simple regex (e.g., from /api/customers/{id})
                            Pattern pattern = Pattern.compile("/api/[^/]+/(\\d+)"); // Matches last numeric segment
                            Matcher matcher = pattern.matcher(exchange.getRequest().getURI().getPath());

                            Long pathId = null;
                            if (matcher.find()) {
                                try {
                                    pathId = Long.parseLong(matcher.group(1));
                                } catch (NumberFormatException e) {
                                    // Not a valid ID in path, or path doesn't match expected pattern
                                    // Log and deny or handle based on strictness.
                                    return denyAccess(exchange, "Access Denied: Invalid path ID format.");
                                }
                            }

                            Object userIdClaim = claims.get("userId"); // Assuming "userId" claim in JWT
                            if (userIdClaim == null || pathId == null || !Long.valueOf(String.valueOf(userIdClaim)).equals(pathId)) {
                                return denyAccess(exchange, "Access Denied: User ID mismatch.");
                            }
                        }

                        // --- 3. Authorization Successful: Mutate Request Headers ---
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-Internal-User-Id", String.valueOf(claims.get("userId")))
                                .header("X-Internal-User-Name", (String) claims.getOrDefault("username", claims.get("sub")))
                                .header("X-Internal-User-Roles", String.valueOf(claims.get("roles"))) // Pass as string
                                .headers(httpHeaders -> httpHeaders.remove("Authorization")) // IMPORTANT: Remove JWT
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());

                    })
                    // If no JwtAuthenticationToken (e.g., public endpoint), just continue
                    .switchIfEmpty(chain.filter(exchange));
        };
    }

	private Mono<Void> denyAccess(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        // Optionally add a response body for more detail
        // response.getHeaders().add("Content-Type", "application/json");
        // return response.writeWith(Mono.just(response.bufferFactory().wrap(message.getBytes())));
        return response.setComplete();
    }
	
	// IMPORTANT: Add this method to tell the factory how to map the arguments from application.properties
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("requiredRole", "matchUserIdToPathId");
    }
}
