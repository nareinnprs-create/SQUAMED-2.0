// 01.10: a plan hides the modules it does not include. The API already refuses a gated module with
// 403 ENTITLEMENT_REQUIRED, so the shell must not offer a link that can only fail.
const NAV_FEATURE: Record<string, string> = {
  ai: 'ai',
  scribe: 'scribe',
};

export interface EntitledNavItem {
  id: string;
}

/**
 * `features === null` means unmanaged (no subscription) or unknown (the entitlement call failed).
 * Both show everything, matching the API's UNMANAGED behaviour: an entitlement outage must not
 * empty the menu.
 */
export function visibleNavItems<T extends EntitledNavItem>(items: T[], features: readonly string[] | null): T[] {
  if (features === null) return items;
  return items.filter((item) => {
    const feature = NAV_FEATURE[item.id];
    return feature === undefined || features.includes(feature);
  });
}
