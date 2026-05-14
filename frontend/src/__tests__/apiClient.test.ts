import { http, HttpResponse } from "msw";
import { api } from "../api/client";
import { useAuthStore } from "../features/auth/authStore";
import { server } from "../test/server";

describe("api client", () => {
  it("injects bearer token for protected requests", async () => {
    useAuthStore.setState({ token: "secret-token", user: { id: "user-1", email: "test@example.com" } });
    server.use(http.get("http://localhost:8082/protected", ({ request }) => HttpResponse.json({ authorization: request.headers.get("authorization") })));
    const response = await api.catalog.get<{ authorization: string }>("/protected");
    expect(response.data.authorization).toBe("Bearer secret-token");
  });

  it("omits bearer token for auth login", async () => {
    useAuthStore.setState({ token: "secret-token", user: { id: "user-1", email: "test@example.com" } });
    server.use(http.post("http://localhost:8081/auth/login", ({ request }) => HttpResponse.json({ authorization: request.headers.get("authorization") })));
    const response = await api.auth.post<{ authorization: string | null }>("/auth/login", { email: "a@b.com", password: "password1" });
    expect(response.data.authorization).toBeNull();
  });
});
