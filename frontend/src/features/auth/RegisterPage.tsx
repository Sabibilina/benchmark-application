import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { register } from "../../api/authApi";
import { AuthForm, type AuthFormValues } from "../../components/forms/AuthForm";
import { useAuthStore } from "./authStore";

export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const mutation = useMutation({
    mutationFn: register,
    onSuccess: (response) => {
      setSession(response);
      navigate("/");
    }
  });
  return (
    <main className="flex min-h-screen items-center justify-center bg-paper px-4">
      <section className="w-full max-w-md rounded-md border border-line bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Create account</h1>
        <p className="mb-6 mt-2 text-sm text-neutral-600">Register with email and password.</p>
        <AuthForm label="Register" onSubmit={(values: AuthFormValues) => mutation.mutate(values)} error={mutation.error?.message} />
        <p className="mt-4 text-sm text-neutral-600">
          Already registered? <Link className="font-medium text-brand" to="/login">Login</Link>
        </p>
      </section>
    </main>
  );
}
