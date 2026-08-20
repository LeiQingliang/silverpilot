# Reference originality review: interactive care landscape

Review date: 2026-08-20

## Outcome

Clear with low-risk similarities. The shipped SilverPilot implementation does not copy reference code, text, images, video, brand marks, or named visual assets. Similarity is limited to common interaction grammar: a real-time 3D landscape, pointer parallax, and selectable scene states.

## Source registry

| Source | Role in review | Reuse decision |
| --- | --- | --- |
| `https://github.com/MengTo/sylva` | User-supplied visual reference | No code, design, or artwork copied. Its README says no license is granted for reuse or redistribution of those materials. |
| `https://github.com/MengTo/Skills` | User-requested workflow and technique reference | Used the installed landscape, originality-audit, and animation-optimization instructions. Repository content is MIT-licensed, but no demo asset was shipped. |
| `src/components/front/home/CareLandscape.vue` | SilverPilot implementation | Original project-specific scene, copy, controls, terrain function, colors, and care-service information model. |
| `src/utils/careLandscapeThree.js` | Runtime boundary | Named exports from the project's npm Three.js dependency; no vendored reference runtime. |

## Category findings

- Text and names: all Chinese copy describes SilverPilot's existing activity, service, health, meal, and Agent capabilities. No reference name or marketing copy is present in the frontend source.
- Numbers and data: the three times are narrative interface states, not imported reference data or claimed operational metrics.
- Images, video, and files: the module loads no image, video, font, model, or remote file. The fallback is CSS; the interactive scene is generated locally.
- Structure: the split editorial copy and visual stage are common product-page patterns. The care timeline, five service nodes, explanatory disclaimer, and navigation actions are specific to this project.
- Visual system: the terrain is generated from a new polar heightfield and project-specific color system. It contains abstract service markers and elevated network arcs, not the reference's named botanical artwork.
- Motion: the module uses restrained pointer parallax, time-state color interpolation, and slow node pulses. It adds pause control, reduced-motion behavior, document-visibility suspension, and direct offscreen RAF shutdown.
- Code and history: source was authored in this workspace without importing the reference repository. The workspace root has no Git metadata, so commit-history comparison is unavailable; source and dependency inventories were used instead.

## Automated evidence inventory

The installed originality skill's inventory helper compared the five files in the homepage component directory with the 12 files in a shallow reference checkout. It reported:

- exact file matches: 0
- historical exact matches: 0 (history intentionally skipped because this workspace snapshot has no Git metadata)
- basename matches: 0
- text overlap leads: 0
- number overlap leads: 0

The machine-readable result is stored at `.qa-artifacts/sylva-originality-evidence.json`. This evidence supplements, rather than replaces, the visual and motion review above.

## Release blockers checked

- Exact code or asset reuse: none found.
- Trademark or reference branding in the shipped frontend: none found.
- Remote hotlinks or unowned media: none found.
- Unlicensed model, texture, font, or video: none used.

Re-run `npm test` and the browser display audit before release; the automated source contract fails if reference names, URLs, or characteristic asset terms enter `src`.
