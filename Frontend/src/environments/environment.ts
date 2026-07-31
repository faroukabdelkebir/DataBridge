// Environnement de PRODUCTION
export const environment = {
  production: true,
  useMock: false,
  // En production, le front est servi derrière le même domaine que Kong
  // (reverse proxy nginx) : chemin relatif pour éviter tout problème CORS.
  gatewayUrl: '',
};
