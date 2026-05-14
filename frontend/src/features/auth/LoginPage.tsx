import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { login } from "../../api/authApi";
import { AuthForm, type AuthFormValues } from "../../components/forms/AuthForm";
import { useAuthStore } from "./authStore";

export function LoginPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      setSession(response);
      navigate("/");
    }
  });
  return (
    <main className="flex min-h-screen items-center justify-center bg-paper px-4">
      <section className="w-full max-w-md rounded-md border border-line bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Sign in</h1>
        <p className="mb-6 mt-2 text-sm text-neutral-600">Use your benchmark account.</p>
        <AuthForm label="Login" onSubmit={(values: AuthFormValues) => mutation.mutate(values)} error={mutation.error?.message} />
        <p className="mt-4 text-sm text-neutral-600">
          No account? <Link className="font-medium text-brand" to="/register">Register</Link>
        </p>
      </section>
    </main>
  );
}
