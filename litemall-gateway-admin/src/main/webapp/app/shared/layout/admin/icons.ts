import {
  FiBox,
  FiChevronRight,
  FiGrid,
  FiMenu,
  FiSettings,
  FiShoppingBag,
  FiSliders,
  FiTag,
  FiTool,
  FiTrendingUp,
  FiUsers,
} from 'react-icons/fi';
import type { ComponentType } from 'react';

// react-icons v5's `IconType` returns `ReactNode`, which the project's React
// typings reject as a JSX element (TS2786). Re-export the icons we use as plain
// component types so they're valid JSX everywhere in the admin shell.
export type IconCmp = ComponentType<{ className?: string }>;

const asCmp = (icon: unknown): IconCmp => icon as IconCmp;

export const IconBox = asCmp(FiBox);
export const IconChevronRight = asCmp(FiChevronRight);
export const IconGrid = asCmp(FiGrid);
export const IconMenu = asCmp(FiMenu);
export const IconSettings = asCmp(FiSettings);
export const IconShoppingBag = asCmp(FiShoppingBag);
export const IconSliders = asCmp(FiSliders);
export const IconTag = asCmp(FiTag);
export const IconTool = asCmp(FiTool);
export const IconTrendingUp = asCmp(FiTrendingUp);
export const IconUsers = asCmp(FiUsers);
