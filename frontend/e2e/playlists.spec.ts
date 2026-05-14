import { expect, test } from "@playwright/test";

test("playlists route is protected", async ({ page }) => {
  await page.goto("/playlists");
  await expect(page).toHaveURL(/\/login$/);
});
