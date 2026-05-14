export function ErrorState({ message }: { message: string }) {
  return <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{message}</div>;
}
