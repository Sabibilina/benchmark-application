import { expect, test } from "@playwright/test";

test("search route is protected", async ({ page }) => {
  await page.goto("/search");
  await expect(page).toHaveURL(/\/login$/);
});
