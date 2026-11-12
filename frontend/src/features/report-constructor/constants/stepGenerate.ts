// Mirrors the backend SLIDES_FROM_SHEET job's actual steps (see ReportGenerationServiceImpl
// #runSlidesFromSheet): read the reviewed sheet → write the narrative copy → fill the deck →
// render the charts. Keep this in lock-step with GEN_STEP_CHECKPOINTS on the page.
export const STAGES = [
    "Reading sheet data",
    "Writing the narrative",
    "Building the slide deck",
    "Building charts",
];
