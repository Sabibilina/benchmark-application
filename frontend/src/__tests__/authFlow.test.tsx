import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "../features/auth/LoginPage";
import { useAuthStore } from "../features/auth/authStore";
import { AppProviders } from "../app/providers";

describe("auth flow", () => {
  it("logs in and keeps the token in memory", async () => {
    const user = userEvent.setup();
    render(
      <AppProviders>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AppProviders>
    );
    await user.type(screen.getByLabelText(/email/i), "test@example.com");
    await user.type(screen.getByLabelText(/password/i), "CorrectHorse123");
    await user.click(screen.getByRole("button", { name: /login/i }));
    expect(await screen.findByText(/sign in/i)).toBeInTheDocument();
    expect(useAuthStore.getState().token).toBe("test-token");
    expect(window.localStorage.getItem("token")).toBeNull();
  });
});
