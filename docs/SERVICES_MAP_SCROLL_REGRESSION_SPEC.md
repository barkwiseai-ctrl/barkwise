# Services Map/List Regression Spec

## Scope
This spec covers the clunky-feeling interactions reported in Services listings when map mode is enabled.

## Scenario 1: Map camera stability
Given the Services screen is in map mode and the user has manually panned/zoomed the map
When provider data refreshes but the effective marker set has not materially changed
Then the camera should not snap back to auto-fit bounds.

## Scenario 2: Scroll handoff after map usage
Given the user toggles from list mode to map mode and back to list mode
When they continue browsing providers
Then list scrolling should remain responsive and allow reaching lower listings without getting stuck.

## Current regression test coverage
- `ServicesScreenRegressionTest.servicesList_mapToggle_keepsListScrollable`
  - Verifies map mode panel appears when map is selected
  - Verifies map panel disappears when returning to list mode
  - Verifies a deep provider row is still reachable via list scrolling after mode toggles
- `ServicesMapAutofitRegressionTest.userInteracted_sameDataSignature_doesNotAutoFit`
  - Verifies map camera does not auto-fit after user interaction when marker data signature is unchanged
