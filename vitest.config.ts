import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['**/*.test.ts', '**/*.spec.ts'],
    globalSetup: './vitest.setup.ts',
    testTimeout: 600000, // 10 minutes
    hookTimeout: 600000, // 10 minutes for setup hooks
    teardownTimeout: 120000, // 2 minutes for cleanup
    pool: 'forks', // Use forks for better isolation
    poolOptions: {
      forks: {
        singleFork: true, // Run tests sequentially to avoid conflicts
        maxForks: 1,
        minForks: 1
      }
    }
  },
});