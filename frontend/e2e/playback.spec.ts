import { expect, test } from "@playwright/test";

test("player bar is present on protected shell after redirect guard", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
});
