export const PAYPAL_DONATION_URL = "https://paypal.me/andamaziz";

export const CEFR_LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const;
export type CEFRLevel = typeof CEFR_LEVELS[number];

export const LEVEL_THRESHOLDS: Record<CEFRLevel, number> = {
  A1: 0,
  A2: 800,
  B1: 1800,
  B2: 3200,
  C1: 4800,
  C2: 6500,
};

export const LEVEL_DESCRIPTIONS: Record<CEFRLevel, string> = {
  A1: 'Beginner - Can understand and use familiar everyday expressions',
  A2: 'Elementary - Can communicate in simple and routine tasks',
  B1: 'Intermediate - Can deal with most situations while traveling',
  B2: 'Upper Intermediate - Can interact with fluency and spontaneity',
  C1: 'Advanced - Can express ideas fluently and spontaneously',
  C2: 'Proficient - Can understand virtually everything heard or read',
};

export const XP_REWARDS = {
  CORRECT: 10,
  WRONG: -5,
} as const;

export const PRACTICE_MODES = {
  FIFTY_QUESTIONS: '50_questions',
  TIMED: 'timed',
} as const;

export const QUESTIONS_PER_SESSION = 50;
export const TIMED_DURATION_SECONDS = 1800; // 30 minutes for 50 questions

export const getCEFRColor = (level: CEFRLevel): string => {
  const colors: Record<CEFRLevel, string> = {
    A1: 'cefr-a1',
    A2: 'cefr-a2',
    B1: 'cefr-b1',
    B2: 'cefr-b2',
    C1: 'cefr-c1',
    C2: 'cefr-c2',
  };
  return colors[level];
};

export const getNextLevel = (currentLevel: CEFRLevel): CEFRLevel | null => {
  const index = CEFR_LEVELS.indexOf(currentLevel);
  if (index < CEFR_LEVELS.length - 1) {
    return CEFR_LEVELS[index + 1];
  }
  return null;
};

export const calculateLevel = (xp: number): CEFRLevel => {
  const levels = [...CEFR_LEVELS].reverse();
  for (const level of levels) {
    if (xp >= LEVEL_THRESHOLDS[level]) {
      return level;
    }
  }
  return 'A1';
};
