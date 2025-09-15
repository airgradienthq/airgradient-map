import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { WildfireController } from './wildfire.controller';
import { WildfireService } from './wildfire.service';

@Module({
  imports: [
    HttpModule.register({
      timeout: 20000,
      maxRedirects: 3,
    }),
  ],
  controllers: [WildfireController],
  providers: [WildfireService],
  exports: [WildfireService],
})
export class WildfireModule {}
