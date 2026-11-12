// Build-time opt-in. Hiding an icon alone must not leave AI routes enabled.
export const AI_ENABLED = import.meta.env.VITE_ENABLE_AI === 'true';
