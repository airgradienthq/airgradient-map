import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { WindDataService } from './wind-data.service';
import { WindDataController } from './wind-data.controller';

@Module({
  imports: [
    HttpModule.register({
      timeout: 600000, // 10 minutes for large files
      maxRedirects: 5,
    }),
  ],
  controllers: [WindDataController],
  providers: [WindDataService],
  exports: [WindDataService],
})
export class WindDataModule {}
