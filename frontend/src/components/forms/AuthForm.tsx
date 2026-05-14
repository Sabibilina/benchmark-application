import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(128)
});

export type AuthFormValues = z.infer<typeof schema>;

export function AuthForm({ label, onSubmit, error }: { label: string; onSubmit: (values: AuthFormValues) => void; error?: string }) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<AuthFormValues>({
    resolver: zodResolver(schema)
  });
  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
      <label className="block text-sm font-medium">
        Email
        <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type="email" {...register("email")} />
        {errors.email ? <span className="text-xs text-red-700">{errors.email.message}</span> : null}
      </label>
      <label className="block text-sm font-medium">
        Password
        <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type="password" {...register("password")} />
        {errors.password ? <span className="text-xs text-red-700">{errors.password.message}</span> : null}
      </label>
      {error ? <p role="alert" className="text-sm text-red-700">{error}</p> : null}
      <button className="focus-ring w-full rounded-md bg-brand px-4 py-2 font-medium text-white" disabled={isSubmitting}>
        {label}
      </button>
    </form>
  );
}
