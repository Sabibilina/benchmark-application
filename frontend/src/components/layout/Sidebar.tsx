import { BarChart3, Compass, Heart, Library, Search } from "lucide-react";
import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "Home", icon: Compass },
  { to: "/search", label: "Search", icon: Search },
  { to: "/catalog", label: "Catalog", icon: Library },
  { to: "/playlists", label: "Playlists", icon: Heart },
  { to: "/history", label: "History", icon: BarChart3 }
];

export function Sidebar() {
  return (
    <aside className="hidden w-60 border-r border-line bg-white p-4 md:block">
      <div className="mb-8 text-lg font-semibold">Benchmark Music</div>
      <nav className="space-y-1">
        {links.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === "/"}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium ${isActive ? "bg-teal-50 text-brand" : "text-neutral-700 hover:bg-neutral-100"}`
            }
          >
            <Icon size={18} /> {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
