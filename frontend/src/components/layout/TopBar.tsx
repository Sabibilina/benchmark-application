import { Bell, LogOut } from "lucide-react";
import { useAuthStore } from "../../features/auth/authStore";
import { NotificationsPanel } from "../../features/notifications/NotificationsPanel";

export function TopBar() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  return (
    <header className="flex items-center justify-between border-b border-line bg-white px-4 py-3">
      <div>
        <p className="text-xs uppercase text-neutral-500">Signed in</p>
        <p className="text-sm font-medium">{user?.email}</p>
      </div>
      <div className="flex items-center gap-2">
        <button className="focus-ring rounded-md border border-line p-2" title="Notifications">
          <Bell size={18} />
        </button>
        <NotificationsPanel />
        <button className="focus-ring rounded-md border border-line p-2" title="Log out" onClick={logout}>
          <LogOut size={18} />
        </button>
      </div>
    </header>
  );
}
