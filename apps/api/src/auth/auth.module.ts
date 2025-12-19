import { Global, Module } from '@nestjs/common';
import { SoftAuthGuard } from './guards/soft-auth.guard';

@Global()
@Module({
  providers: [SoftAuthGuard],
  exports: [SoftAuthGuard],
})
export class AuthModule {}
