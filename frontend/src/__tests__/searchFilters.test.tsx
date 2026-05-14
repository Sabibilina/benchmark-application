import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppProviders } from "../app/providers";
import { SearchPage } from "../features/search/SearchPage";
import { normalizeSearchFilters } from "../features/search/searchFilters";

describe("search filters", () => {
  it("normalizes combined filters", () => {
    expect(normalizeSearchFilters({ q: " blue ", genre: "jazz", bpmMin: "90", bpmMax: "120", year: "2020" })).toEqual({
      q: "blue",
      genre: "jazz",
      bpmMin: 90,
      bpmMax: 120,
      year: 2020,
      page: 0,
      size: 12
    });
  });

  it("renders results for combined filters", async () => {
    const user = userEvent.setup();
    render(<AppProviders><SearchPage /></AppProviders>);
    await user.type(screen.getByLabelText(/search text/i), "blue");
    await user.type(screen.getByLabelText(/genre/i), "jazz");
    await user.type(screen.getByLabelText(/bpm min/i), "90");
    await user.type(screen.getByLabelText(/bpm max/i), "120");
    await user.type(screen.getByLabelText(/year/i), "2020");
    await user.click(screen.getByRole("button", { name: /search/i }));
    expect(await screen.findByText("blue")).toBeInTheDocument();
  });
});
