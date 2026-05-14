import { expect, test } from "@playwright/test";

test("health route is reachable", async ({ page }) => {
  await page.goto("/health");
  await expect(page.getByText("frontend: healthy")).toBeVisible();
});
