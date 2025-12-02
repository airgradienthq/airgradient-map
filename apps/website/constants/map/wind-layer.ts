// Wind speed color scale: turquoise (calm) to dark purple (strong winds)
// Range: 0-15 m/s (0-54 km/h)
// More color stops ensure smooth gradient and proper coverage of the entire range
export const VELOCITY_COLOR_SCALE = [
  '#85d0df', // Very light cyan - calm (0 m/s)
  '#6ec0d3', // Light cyan (1.5 m/s)
  '#56afc7', // Light turquoise (3 m/s)
  '#44a0b8', // Turquoise (4.5 m/s)
  '#358fac', // Medium turquoise (6 m/s)
  '#3d80aa', // Deep turquoise (7.5 m/s)
  '#4770ae', // Blue-purple (9 m/s)
  '#3f65a8', // Purple-blue (10.5 m/s)
  '#3859a5', // Deep blue (12 m/s)
  '#2f529d', // Darker blue (13.5 m/s)
  '#234B85', // Dark blue-purple (15 m/s)
  '#593C9B', // Dark purple (16.5+ m/s - for values exceeding maxVelocity)
  '#402A80' // Very dark purple (18+ m/s - ensures no white rendering)
];
