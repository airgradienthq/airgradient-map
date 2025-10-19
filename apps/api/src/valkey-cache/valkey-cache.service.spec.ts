import { Test, TestingModule } from '@nestjs/testing';
import { ValkeyCacheService } from './valkey-cache.service';

describe('ValkeyCacheService', () => {
  let service: ValkeyCacheService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [ValkeyCacheService],
    }).compile();

    service = module.get<ValkeyCacheService>(ValkeyCacheService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
