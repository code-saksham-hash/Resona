---
name: Nocturne Monolith
colors:
  surface: '#121414'
  surface-dim: '#121414'
  surface-bright: '#383939'
  surface-container-lowest: '#0d0e0f'
  surface-container-low: '#1b1c1c'
  surface-container: '#1f2020'
  surface-container-high: '#292a2a'
  surface-container-highest: '#343535'
  on-surface: '#e3e2e2'
  on-surface-variant: '#c4c7c8'
  inverse-surface: '#e3e2e2'
  inverse-on-surface: '#303031'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c6c6c6'
  primary: '#fdfdfc'
  on-primary: '#2f3131'
  primary-container: '#e0e0e0'
  on-primary-container: '#626363'
  inverse-primary: '#5d5f5f'
  secondary: '#c8c6c5'
  on-secondary: '#303030'
  secondary-container: '#474746'
  on-secondary-container: '#b6b5b4'
  tertiary: '#fffcfb'
  on-tertiary: '#313030'
  tertiary-container: '#e2dfdf'
  on-tertiary-container: '#636262'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1a1c1c'
  on-primary-fixed-variant: '#454747'
  secondary-fixed: '#e4e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1b1c1c'
  on-secondary-fixed-variant: '#474746'
  tertiary-fixed: '#e5e2e1'
  tertiary-fixed-dim: '#c8c6c5'
  on-tertiary-fixed: '#1c1b1b'
  on-tertiary-fixed-variant: '#474746'
  background: '#121414'
  on-background: '#e3e2e2'
  surface-variant: '#343535'
typography:
  display-lg:
    fontFamily: DM Sans
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  display-lg-mobile:
    fontFamily: DM Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: DM Sans
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-sm:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1'
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 8px
  container-padding: 40px
  gutter: 24px
  card-gap: 32px
  mobile-margin: 20px
---

## Brand & Style
The design system embodies a sophisticated, minimalist approach to music discovery and playback. It prioritizes the emotional weight of the album art and the clarity of typography over decorative UI elements. The target audience consists of audiophiles and design enthusiasts who value a "quiet" interface that recedes into the background, allowing the music to take center stage.

The design style is a blend of **High-End Minimalism** and **Tonal Layering**. It avoids the harshness of pure black-and-white contrasts, instead utilizing a narrow spectrum of deep charcoals and soft off-whites to create a premium, velvet-like atmosphere. The aesthetic is intentionally understated, favoring large-scale layout elements and expansive whitespace (negative space) to convey luxury and focus.

## Colors
The palette is strictly monochrome and low-contrast to reduce eye strain and establish a "moody" editorial feel. 

- **Primary Canvas**: The background is set to a deep, slightly warm charcoal (#121212) rather than true black.
- **Surface & Containers**: Elevated components use #1A1A1A or #2A2A2A to create depth through subtle tonal shifts.
- **Typography & Icons**: Most text is rendered in "Soft White" (#E0E0E0), providing sufficient legibility without the glare of #FFFFFF. Secondary information uses mid-range grays to establish hierarchy.
- **Accents**: Interaction states (hover, active) should utilize slight shifts in opacity or subtle grayscale fills rather than introducing hue.

## Typography
The typography system uses a tiered sans-serif approach to balance modern geometry with technical precision.

- **Display & Headlines**: Uses **DM Sans** for its low-contrast, geometric forms. Large display sizes should use tight letter spacing to create a "monolithic" feel in headers and track titles.
- **Body & Metadata**: Uses **Hanken Grotesk** for superior legibility at smaller sizes and a clean, contemporary rhythm.
- **Functional Labels**: Uses **Geist** for utility-based UI (timestamps, bitrates, player controls) to lean into a technical, developer-centric precision.

Scale the typography aggressively; titles should be large and dominant, while metadata should be small and tucked away in the corners or margins.

## Layout & Spacing
The layout follows a **Fluid-Fixed Hybrid** model. Navigation and sidebar elements are fixed, while the primary content area (the "Stage") fluidly expands.

- **Grid**: A 12-column grid is used for desktop, but the layout philosophy is "Container-First." Album grids and card layouts use a heavy 32px gap to maintain the sense of "breathing room."
- **Margins**: Exceptionally large margins (40px+) are used on desktop to create an editorial, gallery-like feel. 
- **Mobile**: On mobile, the grid collapses to 2 columns with reduced padding (20px), but card sizes remain large to ensure a tactile experience.
- **Rhythm**: All spacing is derived from a base-8 scale (8, 16, 24, 32, 48, 64).

## Elevation & Depth
Elevation is achieved through **Tonal Layers** and **Low-Contrast Outlines** rather than traditional shadows.

- **Stacking**: The base background is #121212. Cards and surfaces sit at #1A1A1A. Hover states or active selections elevate further to #2A2A2A.
- **Borders**: Instead of heavy shadows, use 1px solid borders in #2A2A2A to define the perimeter of large cards.
- **Soft Glows**: For the primary "Now Playing" card, a very faint, 40px blur shadow with 5% opacity of the primary text color (#E0E0E0) may be used to simulate a soft backlight.
- **Artist Profiles**: Artist images are always treated as perfect circles, creating a visual contrast against the sharp, rectangular nature of the album art and UI cards.

## Shapes
The design system utilizes **Soft** corners (0.25rem - 0.75rem) to maintain a modern, architectural feel.

- **Album Art & Cards**: Use `rounded-lg` (0.5rem) to soften the large rectangles without appearing "bubbly."
- **Buttons & Chips**: Use `rounded-xl` (0.75rem) or full pill shapes for a tactile, friendly interaction.
- **Artist Images**: These are the only elements that ignore the standard roundedness, utilizing a 100% circular radius to signify human elements vs. technical elements.

## Components
- **Big Bold Cards**: Used for album highlights. These should span multiple grid columns and feature high-resolution artwork with minimal text overlays. Use #1A1A1A for the base.
- **Playback Controls**: Minimalist icons (24px) with high-contrast active states. The play/pause button should be larger and utilize a pill-shaped container with #E0E0E0 background and #121212 icon.
- **Progress Bars**: 4px height. The "track" is #2A2A2A, and the "progress" is #E0E0E0. No "knob" or "thumb" is visible unless the user is hovering over the bar.
- **Lists**: Tracklists should have high row heights (56px+) with subtle dividers using #2A2A2A.
- **Inputs**: Search fields should be "ghost" style—no background fill, just a bottom border in #2A2A2A that thickens or brightens on focus.
- **Artist Profiles**: Displayed as large circular avatars with name labels set in `label-sm` (Geist) directly beneath.