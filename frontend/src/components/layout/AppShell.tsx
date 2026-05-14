import { Outlet } from "react-router-dom";
import { PlayerBar } from "../player/PlayerBar";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";

export function AppShell() {
  return (
    <div className="flex min-h-screen bg-paper">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col pb-28">
        <TopBar />
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
          <Outlet />
        </main>
      </div>
      <PlayerBar />
    </div>
  );
}
