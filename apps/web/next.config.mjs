/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: ['@squamed/ui', '@squamed/design-system', '@squamed/auth', '@squamed/api-client', '@squamed/schemas', '@squamed/domain-types'],
  experimental: {
    serverActions: true,
  },
};

export default nextConfig;