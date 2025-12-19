// Wind speed color scale for dark mode: green-cyan to blue-violet (optimized for dark gray backgrounds)
// Range: 0-15 m/s (0-54 km/h)
// Smooth gradient from greenish-cyan through blue to blue-violet (avoiding pink/magenta)
export const VELOCITY_COLOR_SCALE_DARK = [
  '#48D1CC', // Greenish cyan - calm (0 m/s)
  '#40C8C0', // Light cyan-green (1.5 m/s)
  '#38BFB4', // Cyan-teal (3 m/s)
  '#30B6A8', // Teal (4.5 m/s)
  '#28AD9C', // Deep teal (6 m/s)
  '#3DA8C8', // Cyan-blue (7.5 m/s)
  '#5298D0', // Light blue (9 m/s)
  '#6788D8', // Medium blue (10.5 m/s)
  '#7C78E0', // Blue-violet (12 m/s)
  '#8868D8', // Violet-blue (13.5 m/s)
  '#9458D0', // Deep violet (15 m/s)
  '#9050C8', // Dark blue-violet (16.5+ m/s)
  '#8848C0' // Very dark violet (18+ m/s - ensures visibility)
];

// Wind speed color scale for light mode: turquoise to dark purple (optimized for light backgrounds)
// Range: 0-15 m/s (0-54 km/h)
// More color stops ensure smooth gradient and proper coverage of the entire range
export const VELOCITY_COLOR_SCALE_LIGHT = [
  '#3b8aa1', // Deep cyan - calm (0 m/s)
  '#337c92', // Dark turquoise (1.5 m/s)
  '#2d6f83', // Muted teal (3 m/s)
  '#276174', // Desaturated teal (4.5 m/s)
  '#215366', // Slate teal (6 m/s)
  '#294a7c', // Indigo teal transition (7.5 m/s)
  '#2f428f', // Blue-purple (9 m/s)
  '#2b3a8d', // Deeper blue (10.5 m/s)
  '#273387', // Navy (12 m/s)
  '#232d7f', // Darker navy (13.5 m/s)
  '#1e2874', // Indigo (15 m/s)
  '#43246f', // Dark purple (16.5+ m/s - exceed maxVelocity)
  '#301a60' // Very dark purple (18+ m/s - ensures contrast)
];

export const WIND_LAYER_COLOR_SCALES = {
  dark: VELOCITY_COLOR_SCALE_DARK,
  light: VELOCITY_COLOR_SCALE_LIGHT
};
